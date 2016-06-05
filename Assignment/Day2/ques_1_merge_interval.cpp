//
// Created by Rupesh Choudhary on 6/6/2016.
//

#include<iostream>

using namespace std;

void mergeInterval(int arr[], int num) {
    int i = 1, j = 2;
    int len = num * 2;

    while (j < len) {
        if (j == (len - 2)) {

            if (arr[i] > arr[j]) {
                arr[i] = arr[j + 1];
                break;
            } else {
                i = i + 1;
                arr[i++] = arr[j++];
                arr[i] = arr[j];
                break;
            }
        }
        else if (arr[i] > arr[j]) {
            arr[i] = arr[j + 1];
            j += 2;
        }
        else {
            i = i + 1;
            arr[i++] = arr[j++];
            arr[i] = arr[j++];
        }
    }

    for (j = i + 1; j < len; j++)
        arr[j] = -1;
}

int main() {
    int arr[8] = {1, 5, 2, 6, 5, 8, 7, 10};

    mergeInterval(arr, 4);

    for (int i = 0; i < 8; i++)
        cout << arr[i] << "\t";
    return 0;

}

