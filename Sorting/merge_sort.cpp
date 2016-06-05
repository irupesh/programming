//
// Created by Rupesh Choudhary on 6/5/2016.
//
#include<iostream>

using namespace std;

void merge(int arr[], int start, int mid, int end) {
    int i, j, k;
    int n1 = mid - start + 1;
    int n2 = end - mid;

    int left[n1], right[n2]; //temp

    //copy data to temp arrays
    for (i = 0; i < n1; i++)
        left[i] = arr[start + i];
    for (j = 0; j < n2; j++)
        right[j] = arr[mid + 1 + j];

    //copy to main array
    i = 0;
    j = 0;
    k = start;
    while (i < n1 && j < n2) {
        if (left[i] <= right[j])
            arr[k] = left[i++];
        else
            arr[k] = right[j++];
        k++;
    }

    while (i < n1)
        arr[k++] = left[i++];

    while (j < n2)
        arr[k++] = right[j++];
}

void mergeSort(int arr[], int start, int end) {
    if (start < end) {
        //int mid=(start+end)/2;
        int mid = start + (end - start) / 2; //avoids overflow
        mergeSort(arr, start, mid);
        mergeSort(arr, mid + 1, end);
        merge(arr, start, mid, end);
    }
}

int main() {
    int arr[6] = {15, 10, 12, 4, 9, 5};

    mergeSort(arr, 0, 6 - 1);

    for (int i = 0; i < 6; i++)
        cout << arr[i] << "\t";
    return 0;
}





