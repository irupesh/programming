//
// Created by Rupesh Choudhary on 6/5/2016.
//

#include<iostream>

using namespace std;

int floorSqrt(int num) {
    int low = 0, high = num, mid, sol;

    while (high >= low) {
        mid = (low + high) / 2;

        if (mid * mid == num)
            return mid;
        else if (mid * mid < num) {
            low = mid + 1;
            sol = mid;
        }
        else
            high = mid - 1;
    }
    return sol;
}

int main() {
    int result = floorSqrt(11);
    cout << "SquareRoot is:  " << result;
    return 1;
}
