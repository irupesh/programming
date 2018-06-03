//
// Created by Rupesh Choudhary on 8/30/2016.
//
#include<iostream>
using namespace std;

void printRow(int n) {
    int startNum = n * (n - 1) / 2 + 1;
    while(n > 0) {
        cout << startNum++;
        if(n > 1)
            cout << " * ";
        n--;
    }
}
void print(int n) {
    int i = 1;
    while(i <= n){
        printRow(i++);
        cout<<"\n";
    }
    i--;
    while(i > 0){
        printRow(i--);
        cout<<"\n";
    }
}

int main(){
    /*int n=5;
    int realflag = 1;
    int  num = 1;
    for(int i=1;i<=n;i++){
        int flag = realflag;
        while(flag){
            cout<<num++;
            flag--;
            if(flag){
                cout<<"*";
                flag--;
            }
        }
        realflag +=2;
        cout<<"\n";
    }

    num--;
    realflag -=2;
    num = num - (n-1);
    int display = num;
    for(int i=n;i>=1;i--){
        int flag = realflag;
        while(flag){
            cout<<display;
            display++;
            num--;
            flag--;
            if(flag){
                cout<<"*";
                flag--;
            }
        }
        num++;
        display = num;
        realflag -=2;
        cout<<"\n";
    }*/

    print(4);

}

