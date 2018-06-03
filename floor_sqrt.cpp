#include<iostream>

using namespace std;

int Sqrt(int num) {
    int low = 0, high = num, mid, sol;

    while (high>=low) {
        mid = (low + high)/2;

        if (mid*mid == num)
            return mid;

        else if(mid*mid<num){
            low=mid+1;
            sol=mid;
        }
        else
            high=mid-1;
    }
    return sol;
}

int main() {
    int result = Sqrt(25);
    cout<< result;
    return 0;
}
