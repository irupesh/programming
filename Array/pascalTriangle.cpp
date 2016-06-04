//
// Created by Rupesh Choudhary on 6/5/2016.
//
#include<iostream>

using namespace std;

void pascalTraingle(int n) {
    int arr[n][n];

    for (int i = 0; i < n; i++) {
        for (int j = 0; j <= i; j++) {
            if (i == j || j == 0)   //first and last is 1
                arr[i][j] = 1;
            else
                arr[i][j] = arr[i - 1][j - 1] + arr[i - 1][j];
            cout << arr[i][j];
        }
        cout << "\n";
    }
}

int main() {
    pascalTraingle(5);
}

