cmake_minimum_required(VERSION 3.3)
project(programming)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES Array/boolean_matrix_0_1_row_col.cpp)
add_executable(new ${SOURCE_FILES})