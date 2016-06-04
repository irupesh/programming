cmake_minimum_required(VERSION 3.3)
project(programming)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES floor_sqrt.cpp)
add_executable(new ${SOURCE_FILES})