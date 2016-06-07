//
// Created by Rupesh Choudhary on 6/8/2016.
//

#include<iostream>
#include<queue>

using namespace std;

void printBinary(int num) {
    queue<string> queue1;
    string temp;

    queue1.push("1");

    while (num--) {
        temp = queue1.front();
        queue1.pop();
        cout << temp << endl;
        queue1.push(temp + "0");
        queue1.push(temp + "1");
    }
}

int main() {
    printBinary(10);
    return 0;
}



