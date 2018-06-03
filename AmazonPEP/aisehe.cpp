#include<iostream>

using namespace std;

int main(){
    int n=28;
    int start = 5,count=0;
    n--;
    while(n>0){
        count++;
        n=n-start;
        start+=4;
    }
    cout<<count;
    getch();

}
