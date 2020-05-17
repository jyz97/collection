#pragma once

#include <map>
#include <utility>

class voxelmap {
public:
    double rho;
    std::map<std::tuple<int, int, int>, CGL::Voxel> voxels;
    std::vector<CGL::Vertex *> points;

    voxelmap(std::vector<CGL::Vertex *> points_) : rho(0.0), points(points_) {}

    /**
     * Get all of the points within the 27 neighboring voxels of a point, including
     * the voxel that contains the point.
     */
    std::vector<CGL::Vertex *> get_neighbors(const CGL::Vector3D &position) {
        int x = (int) floor(position.x / (2 * rho));
        int y = (int) floor(position.y / (2 * rho));
        int z = (int) floor(position.z / (2 * rho));
        std::vector<CGL::Vertex *> neighbors;
        for (int a = x - 1; a <= x + 1; a++) {
            for (int b = y - 1; b <= y + 1; b++) {
                for (int c = z - 1; c <= z + 1; c++) {
                    auto it = voxels.find(std::make_tuple(a, b, c));
                    if (it != voxels.end()) {
                        std::copy(it->second.points.begin(),
                                  it->second.points.end(),
                                  std::back_inserter(neighbors));
                    }
                }
            }
        }
        return neighbors;
    }

    void update_rho(double rho_) {
        rho = rho_;
        voxels.clear();
        double sideLength = 2 * rho_;
        for (auto &point : points) {
            CGL::Vector3D position = point->position;
            auto index = std::make_tuple(floor(position.x / sideLength),
                                         floor(position.y / sideLength),
                                         floor(position.z / sideLength));
            auto it = voxels.find(index);
            if (it != voxels.end()) { // can be found in the map
                it->second.add(point);
            } else {
                voxels.insert(std::make_pair(index, CGL::Voxel(point)));
            }
            point->voxel_belong = &it->second;
        }
    }
};
