#pragma once

#include <utility>
#include <array>
#include "vector3D.h"

// A small triangle class where the vertices A, B, C are stored in CCW winding order.
namespace CGL {
    class Edge;

    class Vertex;

    class Triangle {
    public:
        std::array<Vertex *, 3> vertices;
        Vector3D center;

        Triangle(Vertex *A, Vertex *B, Vertex *C, const Vector3D &c);

        std::array<Edge *, 3> get_edges() const;
    };
}