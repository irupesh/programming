//
// Created by Rupesh Choudhary on 8/23/2016.
//
#include <vector>
using namespace std;


int main(){
    int n=10;

    int color[100] = {-1};

    vector< vector<int> > graph;
    graph.resize(10);
    graph[1].push_back(2);
    graph[1].push_back(3);
    graph[3].push_back(6);
    graph[2].push_back(4);
    graph[2].push_back(5);
    graph[5].push_back(7);
    graph[5].push_back(8);
    graph[5].push_back(9);


}

