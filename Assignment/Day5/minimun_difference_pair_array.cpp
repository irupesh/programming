//
// Created by Rupesh Choudhary on 6/9/2016.
//
#include<iostream>

using namespace std;

int minDiff(int arr[], int n) {
    //sort(arr, arr+n);  //sort array

    int diff = 9999;

    for (int i = 0; i < n - 1; i++)
        if (arr[i + 1] - arr[i] < diff)
            diff = arr[i + 1] - arr[i];

    return diff;
}

int main() {
    int arr[] = {1, 3, 5, 15, 16, 24};
    cout << "Minimum difference:- " << minDiff(arr, 6);
    return 0;
}