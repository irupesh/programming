//
// Created by Rupesh Choudhary on 8/14/2016.
//

#include<iostream>

using namespace std;


int maxSumS(int input[],int n){
    int temp[n];
    temp[0]=input[0];
    int max;

    for(int i=1;i<n;i++){
        int max1=-9999;
        for(int j=i-1;j>=0;j--) {
            if (input[j] < input[i]) {
                if (temp[j] > max1)
                    max1 = temp[j];
            }
        }
        if(max1==-9999)
            max1=0;
        temp[i]=input[i]+max1;
    }
    max=temp[0];
    for(int i=1;i<n;i++){
        if(max<temp[i])
            max=temp[i];
    }
   return max;
}

int main(){
    int arr[5]={10,15,11,12,13};
   cout<< maxSumS(arr,5);
}