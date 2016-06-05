//
// Created by Rupesh Choudhary on 6/6/2016.
//
#include<iostream>

using namespace std;


int search(int arr[], int start, int end, int key) {
    if (start > end) return -1;

    int mid = (start + end) / 2;
    if (arr[mid] == key) return mid;

    //sorted
    if (arr[start] <= arr[mid]) {
        if (key >= arr[start] && key <= arr[mid])
            return search(arr, start, mid - 1, key);

        return search(arr, mid + 1, end, key);
    }

    //not sorted
    if (key >= arr[mid] && key <= arr[end])
        return search(arr, mid + 1, end, key);

    return search(arr, start, mid - 1, key);
}

int main() {
    int arr[9] = {4, 5, 6, 7, 8, 9, 1, 2, 3};

    int i = search(arr, 0, 9 - 1, 6);

    cout << "Index: " << i;
}