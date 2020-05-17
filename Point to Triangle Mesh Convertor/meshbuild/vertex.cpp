#include "edge.h"
#include "vertex.h"

namespace CGL {
    void Vertex::update_state() {
        if (adj_edges.empty()) {
            this->state = VertexState::ORPHAN;
        } else if (std::any_of(adj_edges.begin(),
                               adj_edges.end(),
                               [](auto e) { return e->state != EdgeState::INNER; })) {
            this->state = VertexState::FRONT;
        } else {
            this->state = VertexState::INNER;
        }
    }

    bool Vertex::common_edge(Vertex *other, Edge *&common) {
        for (const auto e : adj_edges) {
            if (std::find(other->adj_edges.begin(), other->adj_edges.end(), e) != other->adj_edges.end()) {
                common = e;
                return true;
            }
        }
        return false;
    }
}

