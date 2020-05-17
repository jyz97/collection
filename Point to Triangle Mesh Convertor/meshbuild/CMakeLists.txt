# CMakeList.txt : CMake project for build_voxels+find_candidate, include source and define
# project specific logic here.
#
cmake_minimum_required(VERSION 3.8)
project(meshbuild)

set(CMAKE_CXX_STANDARD 17)

if (${CMAKE_CXX_COMPILER_ID} STREQUAL Clang OR ${CMAKE_CXX_COMPILER_ID} STREQUAL AppleClang)
    set(CMAKE_CXX_FLAGS_RELEASE "-Wall -Wextra -std=c++17 -m64 -O3")
endif ()

if (${CMAKE_CXX_COMPILER_ID} STREQUAL MSVC)
    set(CMAKE_CXX_FLAGS_RELEASE "/std:c++17 /Wall /O2")
endif ()

# Add source to this project's executable.
add_executable(meshbuild "meshbuild.cpp" "edge.cpp" "vertex.cpp" "triangle.cpp")

# TODO: Add tests and install targets if needed.
