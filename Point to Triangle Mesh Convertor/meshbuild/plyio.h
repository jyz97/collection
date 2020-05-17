#pragma once

#include <fstream>
#include <iostream>
#include <memory>
#include <vector>
#include "vector3D.h"
#include "tinyply.h"

#define EPS_F 1e-5

struct Float3 {
    float x, y, z;
};

struct face {
    uint32_t a, b, c;
};

void parse_file(const std::string &filename, std::vector<CGL::Vector3D> &positions, std::vector<face> &faces) {
    std::unique_ptr<std::istream> input_stream;

    input_stream.reset(new std::ifstream(filename, std::ios::binary));
    tinyply::PlyFile file;
    file.parse_header(*input_stream);

    auto parsed_vertices = file.request_properties_from_element("vertex", {"x", "y", "z"});
    auto parsed_faces = file.request_properties_from_element("face", {"vertex_indices"}, 3);

    file.read(*input_stream);

    std::vector<Float3> raw_vertices;
    raw_vertices.resize(parsed_vertices->count);
    std::memcpy(raw_vertices.data(), parsed_vertices->buffer.get(), parsed_vertices->buffer.size_bytes());

    for (size_t i = 0; i < parsed_vertices->count; i++) {
        positions.emplace_back(raw_vertices[i].x, raw_vertices[i].y, raw_vertices[i].z);
    }

    faces.resize(parsed_faces->count);
    std::memcpy(faces.data(), parsed_faces->buffer.get(), parsed_faces->buffer.size_bytes());
}

std::vector<CGL::Vertex>
construct_vertices(const std::vector<CGL::Vector3D> &positions, const std::vector<face> &faces) {
    std::vector<CGL::Vertex> vertices;
    vertices.reserve(positions.size());
    for (const auto &p : positions) {
        vertices.emplace_back(p, CGL::Vector3D());
    }
    for (const auto &f : faces) {
        auto f_norm = cross(positions.at(f.b) - positions.at(f.a),
                            positions.at(f.c) - positions.at(f.b));
        vertices.at(f.a).normal += f_norm;
        vertices.at(f.b).normal += f_norm;
        vertices.at(f.c).normal += f_norm;
    }


    for (auto &v : vertices) {
        v.normal /= (v.normal.norm() + EPS_F);
    }

    return vertices;
}

std::vector<CGL::Vertex> get_vertices(const std::string &filename) {
    std::vector<CGL::Vector3D> positions;
    std::vector<face> faces;
    parse_file(filename, positions, faces);
    auto vertices = construct_vertices(positions, faces);
    for (const auto &f : faces) {
        auto f_norm = cross(positions.at(f.b) - positions.at(f.a),
                            positions.at(f.c) - positions.at(f.b));
        double a_dot = dot(vertices.at(f.a).normal, f_norm);
        double b_dot = dot(vertices.at(f.b).normal, f_norm);
        double c_dot = dot(vertices.at(f.c).normal, f_norm);
        if (a_dot < -EPS_F || b_dot < -EPS_F || c_dot < -EPS_F) {
            std::cout << a_dot << " " << b_dot << " " << c_dot << std::endl;
        }
    }
    return vertices;
}

/**
 * Given the vertices, their normals, faces of each traingle, and edges
 * a ply file is generated for the resulting mesh.
 */
void write_mesh_ply_file(const std::string &filename,
                         std::vector<CGL::Vertex *> &vertices,
                         std::vector<CGL::Triangle *> &mesh) {
    std::filebuf fb_ascii;
    fb_ascii.open(filename, std::ios::out);
    std::ostream outstream_ascii(&fb_ascii);
    if (outstream_ascii.fail()) throw std::runtime_error("failed to open mesh.ply");

    tinyply::PlyFile mesh_file;

    std::vector<Float3> positions;
    std::vector<Float3> normals;
    positions.resize(vertices.size());
    normals.resize(vertices.size());
    for (size_t i = 0; i < vertices.size(); i++) {
        positions[i].x = float(vertices[i]->position.x);
        positions[i].y = float(vertices[i]->position.y);
        positions[i].z = float(vertices[i]->position.z);
        normals[i].x = float(vertices[i]->normal.x);
        normals[i].y = float(vertices[i]->normal.y);
        normals[i].z = float(vertices[i]->normal.z);
    }

    std::map<CGL::Vertex *, uint32_t> vertex_ids;
    for (size_t i = 0; i < vertices.size(); i++) {
        vertex_ids.insert(std::make_pair(vertices[i], i));
    }

    std::vector<face> triangles;
    triangles.reserve(mesh.size());
    for (const auto &t : mesh) {
        triangles.push_back({
                                vertex_ids[t->vertices[0]],
                                vertex_ids[t->vertices[1]],
                                vertex_ids[t->vertices[2]]
                        });
    }

    mesh_file.add_properties_to_element("vertex", {"x", "y", "z"},
                                        tinyply::Type::FLOAT32, vertices.size(),
                                        reinterpret_cast<uint8_t *>(positions.data()),
                                        tinyply::Type::INVALID, 0);

    mesh_file.add_properties_to_element("vertex", {"nx", "ny", "nz"},
                                        tinyply::Type::FLOAT32, normals.size(),
                                        reinterpret_cast<uint8_t *>(normals.data()),
                                        tinyply::Type::INVALID, 0);

    mesh_file.add_properties_to_element("face", {"vertex_indices"},
                                        tinyply::Type::UINT32, triangles.size(),
                                        reinterpret_cast<uint8_t *>(triangles.data()),
                                        tinyply::Type::UINT8, 3);

    // Write an ASCII file
    mesh_file.write(outstream_ascii, false);
}