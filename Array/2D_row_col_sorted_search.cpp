//
// Created by Rupesh Choudhary on 6/5/2016.
//
#include <iostream>

using namespace std;

int search(int mat[4][4], int row, int key) {
    int i = 0, j = row - 1;  //sindex at top right element
    while (i < row && j >= 0) {
        if (mat[i][j] == key) {
            cout << "\n Found at " << i + 1 << " " << j + 1;
            return 1;
        }
        if (mat[i][j] > key)
            j--;
        else
            i++;
    }
    cout << "\n Element not found";
    return 0;
}

int main() {
    int arr[4][4] = {{14, 20, 38, 44},
                     {15, 22, 39, 45},
                     {17, 35, 40, 48},
                     {40, 41, 42, 90},
    };
    search(arr, 4, 22);
    return 0;
}