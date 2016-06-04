//
// Created by Rupesh Choudhary on 6/5/2016.
//
#include<iostream>

using namespace std;

//print Matrix
void printMatrix(bool mat[][4], int row, int column) {
    for (int i = 0; i < row; i++) {
        for (int j = 0; j < column; j++) {
            cout << mat[i][j];
        }
        cout << "\n";
    }
}

void modifyMatrix(bool mat[][4], int r, int c) {
    bool row[r];
    bool col[c];

    //intialize
    for (int i = 0; i < r; i++) {
        row[i] = 1;
    }

    for (int i = 0; i < c; i++) {
        col[i] = 1;
    }

    //checking row and col
    for (int i = 0; i < r; i++) {
        for (int j = 0; j < c; j++) {
            if (mat[i][j] == 0) {
                row[i] = 0;
                col[j] = 0;
            }
        }
    }

    //Final change
    for (int i = 0; i < r; i++) {
        for (int j = 0; j < c; j++) {
            if (row[i] == 0 || col[j] == 0) {
                mat[i][j] = 0;
            }
        }
    }
}

int main() {
    bool mat[3][4] = {{1, 0, 0, 1},
                      {1, 1, 1, 1},
                      {0, 1, 0, 1},
    };
    cout << "Original Matrix \n";
    printMatrix(mat, 3, 4);
    modifyMatrix(mat, 3, 4);
    cout << "Matrix after modification \n";
    printMatrix(mat, 3, 4);
    return 0;
}