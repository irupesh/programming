//
// Created by Rupesh Choudhary on 6/7/2016.
//

#include<iostream>
#include<stack>

using namespace std;

stack<int> stack1;

void sortedIns(int x) {
    int y;
    if (stack1.empty() || stack1.top() < x) {
        stack1.push(x);
        return;
    }
    else {
        y = stack1.top();
        stack1.pop();
        sortedIns(x);
        stack1.push(y);
    }
}

void sort() {
    int x;
    if (!stack1.empty()) {
        x = stack1.top();
        stack1.pop();
        sort();
        sortedIns(x);
    }
}


int main() {
    stack1.push(9);
    stack1.push(1);
    stack1.push(8);
    stack1.push(5);
    sort();

    while (!stack1.empty()) {
        cout << stack1.top() << endl;
        stack1.pop();
    }
}


