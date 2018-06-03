#include<iostream>
#include<vector>
#include<queue>

using namespace std;

vector<int>graph[500];
int visited[500]={0};

void bfs(int root)
{
    queue<int> qu;
    qu.push(root);

    while(!qu.empty()){
        int v = qu.front();
        qu.pop();

        if(visited[v]==0){
          cout<<v<<endl;
          visited[v]=1;

          int sz  = graph[v].size();

          for(int i=0;i<sz;i++){
            qu.push(graph[v][i]);
        }
    }
    }
}

int main(){
   int vertices,edges,v1,v2;
   cin>>vertices>>edges;
   for(int i=0;i<edges;i++){
    cin>>v1>>v2;
    graph[v1].push_back(v2);
    graph[v2].push_back(v1);
   }
  bfs(0);
}
