// TODO: further edition is necessary, won't fulfill the expand Triangle algrithm

#pragma once

#include <ostream>
#include <cmath>
#include "vector3D.h"
#include "triangle.h"

namespace CGL {
    class Vertex;

    enum class EdgeState {
        ACTIVE,
        BOUNDARY,
        INNER
    };

    /**
     * Defines 3D vectors.
     */
    class Edge {
    public:
        EdgeState state;
        Vertex *source;
        Vertex *target;
        std::array<Triangle *, 2> adj_triangles{};
        size_t num_adjacent;

        // constructor
        Edge(Vertex *src, Vertex *tgt);

        void add_adjacent_triangle(Triangle *t);
    }; // class Edge

} // namepace CGL