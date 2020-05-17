#include <stdexcept>

#include "triangle.h"
#include "vertex.h"
#include "edge.h"

namespace CGL {
    Triangle::Triangle(Vertex *A, Vertex *B, Vertex *C, const Vector3D &c) : center(c) {
        vertices = {A, B, C};
        Edge *AB, *BC, *CA;
        if (!A->common_edge(B, AB)) {
            AB = new Edge(A, B);
        }
        if (!B->common_edge(C, BC)) {
            BC = new Edge(B, C);
        }
        if (!C->common_edge(A, CA)) {
            CA = new Edge(C, A);
        }
        AB->add_adjacent_triangle(this);
        BC->add_adjacent_triangle(this);
        CA->add_adjacent_triangle(this);
        A->update_state();
        B->update_state();
        C->update_state();
        A->voxel_belong->used = true;
        B->voxel_belong->used = true;
        C->voxel_belong->used = true;
    }

    std::array<Edge *, 3> Triangle::get_edges() const {
        std::array<Edge *, 3> edges = {nullptr, nullptr, nullptr};
        if (!vertices[0]->common_edge(vertices[1], edges.at(0)) ||
            !vertices[1]->common_edge(vertices[2], edges.at(1)) ||
            !vertices[2]->common_edge(vertices[0], edges.at(2))) {
            throw std::domain_error("One or more triangle edges doesn't exist!");
        }
        return edges;
    }
}