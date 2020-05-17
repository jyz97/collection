cmake_minimum_required(VERSION 3.16)
project(plyconvert)

set(CMAKE_CXX_STANDARD 11)

set(CMAKE_CXX_FLAGS_RELEASE "-Wall -Wextra -O3")

add_executable(plyconvert convert.cpp)