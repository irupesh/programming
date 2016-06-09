//
// Created by Rupesh Choudhary on 6/9/2016.
//
#include<iostream>

using namespace std;


int max(int num1, int num2) {
    return (num1 > num2) ? num1 : num2;
}

int FindMaxSum(int arr[], int n) {
    int incl = arr[0];
    int excl = 0;
    int excl_new;

    for (int i = 1; i < n; i++) {
        excl_new = max(incl, excl);
        incl = excl + arr[i];
        excl = excl_new;
    }

    return max(incl, excl);
}

int main() {
    int arr[] = {5, 5, 10, 40, 50, 35};
    cout << FindMaxSum(arr, 6);
    return 0;
}