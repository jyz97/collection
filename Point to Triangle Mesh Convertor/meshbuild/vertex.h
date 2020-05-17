// vertex.h : define the Vertex class

// TODO: further edition is necessary, won't fulfill the expand Triangle algrithm

#pragma once

#include <ostream>
#include <cmath>
#include <vector>
#include "vector3D.h"
#include "voxel.h"

namespace CGL {
    class Edge;

    class Triangle;

    enum class VertexState {
        ORPHAN,
        FRONT,
        INNER
    };

    /**
     * Defines 3D vectors.
     */
    class Vertex {
    public:
        Vector3D position;
        Vector3D normal;
        VertexState state;
        Voxel* voxel_belong;
        std::vector<Edge *> adj_edges;
        std::vector<Triangle *> adj_triangles;

        Vertex() : position(), normal(), state(VertexState::ORPHAN), adj_edges() {}

        Vertex(const Vector3D &p, const Vector3D &n) : position(p), normal(n), state(VertexState::ORPHAN),
                                                       adj_edges() {}

        Vertex(const Vector3D &p, const Vector3D &n, VertexState s) : position(p), normal(n), state(s), adj_edges() {}

        inline bool operator==(const Vertex &v) const {
            return (this->position - v.position).norm() == 0.0;
        }

        void update_state();

        bool common_edge(Vertex *other, Edge *&common);
    }; // class Vertex

} // namepace CGL