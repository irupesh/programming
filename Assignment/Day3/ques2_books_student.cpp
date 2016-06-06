//
// Created by Rupesh Choudhary on 6/7/2016.
//

#include<iostream>

using namespace std;

bool isPossible(int arr[], int size, int student, int curMin) {

    int studentsRequired = 1;
    int curSum = 0;

    for (int i = 0; i < size; i++) {

        if (arr[i] > curMin) return false;

        if (curSum + arr[i] > curMin) {

            studentsRequired++;
            curSum = arr[i];
            if (studentsRequired > student) return false;
        }
        else {
            curSum += arr[i];
        }
    }
    return true;
}

int solve(int arr[], int size, int student) {

    int sum = 0;
    if (size < student) return -1;

    for (int i = 0; i < size; i++)
        sum += arr[i];


    int start = 0;
    int end = sum, mid;
    int ans = 99999;

    while (start <= end) {

        mid = (start + end) / 2;

        if (isPossible(arr, size, student, mid)) {

            ans = min(ans, mid);
            end = mid - 1;
        }

        else {
            start = mid + 1;
        }
    }
    return ans;

}

int main() {

    int arr[] = {12, 34, 67, 90};
    int n = sizeof arr / sizeof arr[0];
    int m = 2;

    cout << solve(arr, n, m) << endl;
}