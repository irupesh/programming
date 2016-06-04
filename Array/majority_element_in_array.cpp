//
// Created by Rupesh Choudhary on 3/17/2016.
//

#include <iostream>

using namespace std;

int main() {
    int arr[] = {1, 1, 2, 7, 3, 2, 7, 2, 2};
    int candidate, count = 0, n = 8;

    for (int i = 0; i < 8; ++i) {
        if (count == 0) {
            candidate = arr[i];
            count = 1;
        }
        else {
            if (candidate == arr[i])
                count++;
            else
                count--;
        }
    }

    count = 0;

    for (int i = 0; i < 8; ++i) {
        if (candidate == arr[i])
            count++;
    }

    if (count > n / 2)
        cout << "Majority element is :- " << candidate;
    else
        cout << "No majority element exist";
}

