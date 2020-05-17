#include <fstream>
#include <iostream>
#include <memory>
#include <vector>

#define TINYPLY_IMPLEMENTATION

#include "tinyply.h"

#define EPS_F 1e-5

struct vertex {
    float x, y, z;
};

struct vec {
    float x, y, z;
};

struct face {
    uint32_t a, b, c;
};

inline vec offset(vertex from, vertex to) {
    return {to.x - from.x, to.y - from.y, to.z - from.z};
}

inline vec cross(vec a, vec b) {
    return {a.y * b.z - a.z * b.y,
            a.x * b.z - a.z * b.x,
            a.y * b.z - a.z * b.y};
}

inline float dot(vec a, vec b) {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}

inline vec area_scaled_face_normal(const face &f, const std::vector<vertex> &vertices) {
    vertex A = vertices.at(f.a);
    vertex B = vertices.at(f.b);
    vertex C = vertices.at(f.c);

    vec AB = offset(A, B);
    vec BC = offset(B, C);

    return cross(AB, BC);
}

void parse_file(const std::string &filename, std::vector<vertex> &vertices, std::vector<face> &faces) {
    std::unique_ptr<std::istream> input_stream;

    input_stream.reset(new std::ifstream(filename, std::ios::binary));
    tinyply::PlyFile file;
    file.parse_header(*input_stream);

    auto parsed_vertices = file.request_properties_from_element("vertex", {"x", "y", "z"});
    auto parsed_faces = file.request_properties_from_element("face", {"vertex_indices"}, 3);

    file.read(*input_stream);

    vertices.resize(parsed_vertices->count);
    std::memcpy(vertices.data(), parsed_vertices->buffer.get(), parsed_vertices->buffer.size_bytes());

    faces.resize(parsed_faces->count);
    std::memcpy(faces.data(), parsed_faces->buffer.get(), parsed_faces->buffer.size_bytes());
}

std::vector<vec>
area_weighted_vertex_normals(const std::vector<vertex> &vertices, const std::vector<face> &faces) {
    auto normals = std::vector<vec>();
    normals.resize(vertices.size());
    std::fill(normals.begin(), normals.end(), vec{0, 0, 0});
    for (const auto &f : faces) {
        vec f_norm = area_scaled_face_normal(f, vertices);

        normals.at(f.a).x += f_norm.x;
        normals.at(f.a).y += f_norm.y;
        normals.at(f.a).z += f_norm.z;

        normals.at(f.b).x += f_norm.x;
        normals.at(f.b).y += f_norm.y;
        normals.at(f.b).z += f_norm.z;

        normals.at(f.c).x += f_norm.x;
        normals.at(f.c).y += f_norm.y;
        normals.at(f.c).z += f_norm.z;
    }

    for (auto &n : normals) {
        float size = sqrt(n.x * n.x + n.y * n.y + n.z * n.z);
        n.x /= (size + EPS_F);
        n.y /= (size + EPS_F);
        n.z /= (size + EPS_F);
    }

    return normals;
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        std::cerr << "Usage: ./convert [input_file] [output_file]" << std::endl;
        return EXIT_FAILURE;
    }

    std::string input_file(argv[1]);
    std::vector<vertex> vertices;
    std::vector<face> faces;
    parse_file(input_file, vertices, faces);

    auto normals = area_weighted_vertex_normals(vertices, faces);

    for (const auto &f : faces) {
        float a_dot = dot(normals.at(f.a), area_scaled_face_normal(f, vertices));
        float b_dot = dot(normals.at(f.b), area_scaled_face_normal(f, vertices));
        float c_dot = dot(normals.at(f.c), area_scaled_face_normal(f, vertices));
        if (a_dot < -EPS_F || b_dot < -EPS_F || c_dot < -EPS_F) {
            std::cout << a_dot << " " << b_dot << " " << c_dot << std::endl;
        }
    }

    std::ofstream out(argv[2]);
    out << vertices.size() << std::endl;
    for (size_t i = 0; i < vertices.size(); i++) {
        out << vertices.at(i).x << " " << vertices.at(i).y << " " << vertices.at(i).z << " "
            << normals.at(i).x << " " << normals.at(i).y << " " << normals.at(i).z << std::endl;
    }
    out.close();

    return EXIT_SUCCESS;
}
