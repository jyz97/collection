#ifndef CGL_VOXEL_H
#define CGL_VOXEL_H

#include <ostream>
#include <cmath>
#include <vector>
#include <list>

namespace CGL {
    class Voxel {
    public:

        std::vector<Vertex *> points;

        bool used;

        Voxel() {
            this->points = {};
            used = false;
        };

        Voxel(Vertex *p) {
            (this->points).push_back(p);
            used = false;
        };

        ~Voxel() {};

        inline void add(Vertex *p) {
            (this->points).push_back(p);
        }


    }; // class Voxel
    // prints components
    std::ostream &operator<<(std::ostream &os, const Voxel &v);

} // namespace CGL
#endif // CGL_VOXEL_H