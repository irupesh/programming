//
// Created by Rupesh Choudhary on 10/3/2016.
//
#include<iostream>

using namespace std;

int input[100][100]={0};
int visited[100][100] = {0};

void visit(int i,int j){
    if(input[i+1][j] == 1 && visited[i+1][j] == 0){
        visited[i+1][j] == 1;
        visit(i+1,j);
    }
    if(input[i-1][j] == 1 && visited[i-1][j] == 0){
        visited[i-1][j] == 1;
        visit(i-1,j);
    }
    if(input[i][j+1] == 1 && visited[i][j+1] == 0){
        visited[i][j+1] == 1;
        visit(i,j+1);
    }
    if(input[i][j-1] == 1 && visited[i][j-1] == 0){
        visited[i][j-1] == 1;
        visit(i,j-1);
    }
}

int main(){
    int n,m,iland=0;

    cin>>n>>m;

    for(int i=1;i<=n;i++){
        for(int j=1;j<=m;j++){
            cin>>input[i][j];
        }
    }

    for(int i=1;i<=n;i++){
        for(int j=1;j<=m;j++){
            if(input[i][j] == 1 && visited[i][j] == 0){
                iland++;
                visited[i][j] == 1;
                visit(i,j);
            }
        }
    }
    cout<<iland;
}
