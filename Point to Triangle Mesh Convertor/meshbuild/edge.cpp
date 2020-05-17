#include <stdexcept>

#include "edge.h"
#include "vertex.h"

namespace CGL {
    Edge::Edge(Vertex *src, Vertex *tgt) {
        this->state = EdgeState::ACTIVE;
        this->source = src;
        this->target = tgt;
        this->num_adjacent = 0;
        this->adj_triangles = {nullptr, nullptr};

        source->adj_edges.push_back(this);
        target->adj_edges.push_back(this);
    }

    void Edge::add_adjacent_triangle(Triangle *t) {
        if (adj_triangles[0] == t || adj_triangles[1] == t) {
            return;
        } else if (num_adjacent == 2) {
            throw std::domain_error("Too many adjacent triangles for this edge!");
        }
        adj_triangles[num_adjacent++] = t;
        state = num_adjacent == 1 ? EdgeState::ACTIVE : EdgeState::INNER;
    }
}

