//
// Created by Rupesh Choudhary on 10/3/2016.
//
#include <iostream>

using namespace std;

int main(){
    int arr[11] = {1,1,1,1,1,1,1,1,1,0,0};

    int temp[11] = {0};


    for(int i=1;i<11;i++){
        int min1 = 999999;
        for(int j=i-1;j>=0;j--){
            if(temp[j] == -1)
                continue;

            if((i-j)<=arr[j]){
                min1 = min(min1,temp[j]+1);
            }
        }
        if(min1 == 999999)
            temp[i] = -1;
        else
            temp[i] = min1;
    }

    cout<<temp[10];

}
