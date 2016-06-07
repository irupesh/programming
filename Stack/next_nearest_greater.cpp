//
// Created by Rupesh Choudhary on 6/7/2016.
//

#include<iostream>
#include<stack>

using namespace std;

stack<int> stack1;

void nextGreater(int arr[], int size) {
    int nge[size];

    for (int i = 0; i < size; i++) {
        if (stack1.empty() || arr[i] < arr[stack1.top()]) {
            stack1.push(i);
        }
        else {
            while ((arr[i] > arr[stack1.top()])) {
                nge[stack1.top()] = arr[i];
                stack1.pop();
                if (stack1.empty()) break;
            }
            stack1.push(i);
        }
    }

    while (!stack1.empty()) {
        nge[stack1.top()] = -99999;
        stack1.pop();
    }

    cout << "\nNext greater element array\n";
    for (int i = 0; i < size; i++) {
        cout << nge[i] << endl;
    }
}

int main() {
    int arr[] = {11, 4, 9, 33, 8, 64, 2};
    nextGreater(arr, 7);

}
