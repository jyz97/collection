#include <algorithm>
#include <stack>
#include <iostream>

#include "vector3D.h"
#include "edge.h"
#include "voxel.h"
#include "vertex.h"
#include "voxelmap.h"

#define TINYPLY_IMPLEMENTATION

#include "plyio.h"

#define M_PI (3.14159265358979323846264338327950288)

std::vector<CGL::Vertex *> get_sorted_nearby_orphan_points(voxelmap &voxelMap, const CGL::Vertex *p) {
    auto nearby_points = voxelMap.get_neighbors(p->position);
    std::vector<CGL::Vertex *> orphan_vertices;
    std::copy_if(nearby_points.begin(), nearby_points.end(), std::back_inserter(orphan_vertices),
                 [](CGL::Vertex *v) { return v->state == CGL::VertexState::ORPHAN; });
    std::sort(orphan_vertices.begin(), orphan_vertices.end(), [&](CGL::Vertex *a, CGL::Vertex *b) {
        double p1_dist = (a->position - p->position).norm();
        double p2_dist = (b->position - p->position).norm();
        return p1_dist < p2_dist;
    });
    return orphan_vertices;
}

/**
 * Three vertices are compatible if the normal to the triangle they form has positive
 * scalar product with each of the normals of the vertices. We assume a clockwise
 * winding order in the points (p, q, s).
 */
bool is_compatible(const CGL::Vertex *p, const CGL::Vertex *q, const CGL::Vertex *s) {
    CGL::Vector3D triangle_normal = cross(q->position - p->position, s->position - q->position).unit();

    return dot(triangle_normal, p->normal) > 0 &&
           dot(triangle_normal, q->normal) > 0 &&
           dot(triangle_normal, s->normal) > 0;
}

/**
 * From Section 4 of https://www.ipol.im/pub/art/2014/81/article.pdf
 */
bool get_ball_center(CGL::Vertex *A, CGL::Vertex *B, CGL::Vertex *C, double radius, CGL::Vector3D &center) {
    double a = (C->position - B->position).norm();
    double b = (A->position - C->position).norm();
    double c = (B->position - A->position).norm();
    double alpha = a * a * (b * b + c * c - a * a);
    double beta = b * b * (a * a + c * c - b * b);
    double gamma = c * c * (a * a + b * b - c * c);
    double total = alpha + beta + gamma;
    alpha /= total;
    beta /= total;
    gamma /= total;

    if (alpha < 0 || alpha > 1 || beta < 0 || beta > 1 || gamma < 0 || gamma > 1) {
        return false;
    }

    CGL::Vector3D circumcenter = alpha * A->position + beta * B->position + gamma * C->position;
    double sq_circumradius = (a * a * b * b * c * c) /
                             ((a + b + c) * (b + c - a) * (c + a - b) * (a + b - c));

    if (radius * radius - sq_circumradius < 0) {
        return false;
    }

    auto normal = cross(B->position - A->position, C->position - B->position).unit();
    if (dot(normal, A->normal) < 0) {
        normal *= -1;
    }

    center = circumcenter + sqrt(radius * radius - sq_circumradius) * normal;
    return true;
}

/**
 * Given a set of points the center of the ball is calculated and then check if
 * points are in empty ball configuration by making sure there are no other points
 * within the ball, except for the three points.
 *
 * Returns true is points are in empty ball config
 */
bool is_empty_ball_config(CGL::Vertex *p,
                          CGL::Vertex *q,
                          CGL::Vertex *s,
                          double radius,
                          const std::vector<CGL::Vertex *> &nearby) {
    CGL::Vector3D center;
    if (!get_ball_center(p, q, s, radius, center)) {
        return false;
    }
    for (const auto v : nearby) {
        if (v == p || v == q || v == s) {
            continue;
        }
        if ((center - v->position).norm() < radius) {
            return false;
        }
    }
    return true;
}

bool find_seed_triangle(voxelmap &voxelMap, CGL::Triangle *&seed) {
    for (auto &elem : voxelMap.voxels) {
        if (elem.second.used) {
            continue;
        }
        for (const auto &p : elem.second.points) {
            if (p->state != CGL::VertexState::ORPHAN) {
                continue;
            }
            auto nearby_orphan_points = get_sorted_nearby_orphan_points(voxelMap, p);
            for (const auto &q : nearby_orphan_points) {
                for (const auto &s : nearby_orphan_points) {
                    if (p == q || p == s || q == s) {
                        continue;
                    }
                    if (is_compatible(p, q, s) && is_empty_ball_config(p, q, s, voxelMap.rho, nearby_orphan_points)) {
                        CGL::Vector3D center;
                        get_ball_center(p, q, s, voxelMap.rho, center);
                        seed = new CGL::Triangle(p, q, s, center);
                        elem.second.used = true;
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

bool is_bad_candidate(CGL::Edge *e, CGL::Vertex *p) {
    CGL::Edge *tp, *ps;
    if (e->target->common_edge(p, tp)) {
        if (tp->state == CGL::EdgeState::INNER) {
            return true;
        }
    }
    if (p->common_edge(e->source, ps)) {
        if (ps->state == CGL::EdgeState::INNER) {
            return true;
        }
    }
    return false;
}

// return true if the candidate and the corresponding center are assigned values
bool find_candidate(CGL::Edge *e,
                    CGL::Vertex *&candidate,
                    CGL::Vector3D &newCenter,
                    voxelmap &voxelMap) {
    CGL::Vector3D midpoint = (e->source->position + e->target->position) / 2;

    // find 27 neighbouring voxels of midpoint
    auto neighbors = voxelMap.get_neighbors(midpoint);

    // for each point in 27 voxels calculate the potential center
    // find the center that is closest to the trajactory
    double minTheta = 2.0 * M_PI;
    bool updated = false;

    for (auto p : neighbors) {
        if (p->state == CGL::VertexState::INNER ||
            is_bad_candidate(e, p) ||
            std::any_of(e->adj_triangles[0]->vertices.begin(), e->adj_triangles[0]->vertices.end(),
                        [=](auto v) { return p == v; })) {
            continue;
        }

        CGL::Vector3D center;
        // cannot find a center of the ball that contains the edge and this point
        if (!get_ball_center(e->source, e->target, p, voxelMap.rho, center)) {
            continue;
        }

        // compute the angle
        // unit vector from midpoint points to the new center found
        CGL::Vector3D mToNewCenter = (center - midpoint).unit();
        // unit vector from midpoint points to the old center
        CGL::Vector3D mToOldCenter = (e->adj_triangles[0]->center - midpoint).unit();
        CGL::Vector3D edge = e->target->position - e->source->position;
        // reduce floating point error
        double cosineTheta = std::clamp(dot(mToNewCenter, mToOldCenter), double(-1), double(1));
        double theta = acos(cosineTheta);
        if (dot(cross(mToOldCenter, mToNewCenter), edge) < 0) { // angle actually on the other half circle
            theta = 2.0 * M_PI - theta;
        }

        auto near_center = voxelMap.get_neighbors(center);
        int num_other_points_in_ball = std::count_if(near_center.begin(), near_center.end(), [&](CGL::Vertex *v) {
            double dist_to_center = (v->position - center).norm();
            return dist_to_center < voxelMap.rho && v != p && v != e->source && v != e->target;
        });
        if (num_other_points_in_ball != 0) {
            continue;
        }

        // pick point with corresponding center has the smallest angle on the trajactory
        if (theta < minTheta) {
            minTheta = theta;
            candidate = p;
            newCenter = center;
            updated = true;
        }
    }

    return updated;
}

/**
 * Expands the mesh by appending new triangle faces
 */
void expand_triangulation(CGL::Triangle *seed, voxelmap &voxelMap, std::vector<CGL::Triangle *> &mesh) {
    std::stack<CGL::Edge *> front;
    for (const auto e : seed->get_edges()) {
        front.push(e);
    }
    mesh.push_back(seed);
    while (!front.empty()) {
        auto edge = front.top();
        front.pop();
        if (edge->state != CGL::EdgeState::ACTIVE) {
            continue;
        }
        CGL::Vertex *candidate;
        CGL::Vector3D newCenter;
        if (!find_candidate(edge, candidate, newCenter, voxelMap)) {
            edge->state = CGL::EdgeState::BOUNDARY;
            continue;
        }
        auto new_triangle = new CGL::Triangle(edge->source, edge->target, candidate, newCenter);
        mesh.push_back(new_triangle);
        CGL::Edge *candidate_to_s, *t_to_candidate;
        // Both of the following should be non-null due to the call to the Triangle constructor above.
        candidate->common_edge(edge->source, candidate_to_s);
        edge->target->common_edge(candidate, t_to_candidate);

        if (candidate_to_s->state == CGL::EdgeState::ACTIVE) {
            front.push(candidate_to_s);
        }
        if (t_to_candidate->state == CGL::EdgeState::ACTIVE) {
            front.push(t_to_candidate);
        }
    }
}

void output_progress_bar(double progress) {
    const int bar_width = 80;
    std::cout << "[";
    int pos = int(bar_width * progress);
    std::cout << std::string(pos, '=') << ">"
              << std::string(bar_width - pos - 1, ' ') << "] "
              << int(progress * 100.0) << "%" << std::endl;
}

int main(int argc, char *argv[]) {
    if (argc < 5) {
        std::cerr << "Usage: ./meshbuild [input_file] [output_file] [max_iters] [rhos...]" << std::endl;
        return EXIT_FAILURE;
    }

    const int max_iters = std::stoi(std::string(argv[3]));

    std::vector<double> rhos;
    for (int i = 4; i < argc; i++) {
        rhos.push_back(std::stod(std::string(argv[i])));
    }

    auto vertices = get_vertices(std::string(argv[1]));
    std::vector<CGL::Vertex *> vertex_pointers;
    std::transform(vertices.begin(), vertices.end(), std::back_inserter(vertex_pointers),
                   [](CGL::Vertex &v) {
                       return &v;
                   });
    voxelmap voxelMap(vertex_pointers);

    std::vector<CGL::Triangle *> mesh;
    for (double rho : rhos) {
        voxelMap.update_rho(rho);
        CGL::Triangle *seed;
        int i = 0;
        while (i < max_iters && find_seed_triangle(voxelMap, seed)) {
            expand_triangulation(seed, voxelMap, mesh);
            output_progress_bar(double(i) / max_iters);
            i++;
        }
        std::cout << "With rho=" << rho << ", constructed a mesh of size " << mesh.size() << std::endl;
    }

    for (auto t : mesh) {
        if (is_compatible(t->vertices[0], t->vertices[1], t->vertices[2])) {
            // XCode PLY viewer expects CW ordering of vertices.
            std::swap(t->vertices.at(0), t->vertices.at(1));
        }
    }

    write_mesh_ply_file(std::string(argv[2]), vertex_pointers, mesh);
    return 0;
}
