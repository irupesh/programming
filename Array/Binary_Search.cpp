//
// Created by Rupesh Choudhary on 6/4/2016.
//
#include<iostream>

using namespace std;

int binarySearch(int arr[], int size, int key) {
    int low = 0, high = size - 1, mid;

    while (high >= low) {
        mid = (low + high) / 2;

        if (arr[mid] == key)
            return mid;
        else if (arr[mid] > key)
            high = mid - 1;
        else
            low = mid + 1;
    }
    return -1;
}

int main() {
    int arr[6] = {1, 6, 7, 9, 10, 85};
    int pos = binarySearch(arr, 6, 10);
    cout << "Element found at: " << pos + 1 << " position";
    return 1;
}

