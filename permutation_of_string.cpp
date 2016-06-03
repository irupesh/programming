//
// Created by Rupesh Choudhary on 3/18/2016.
//

//function to interchange the position of the character
#include <iostream>
#include <string.h>

using namespace std;

void swap(char *first, char *second) {
    char temp;
    temp = *first;
    *first = *second;
    *second = temp;
}

//function to permutate using backtrack

void permute(char *str, int start, int end) {
    if (start == end)
        cout << str << endl;
    else {
        for (int i = start; i <= end; ++i) {
            swap((str + start), (str + i));
            permute(str, start + 1, end);
            swap((str + start), (str + i)); //backtraking
        }
    }

}

int main() {
    char str[] = "ABC";
    int n = strlen(str);
    permute(str, 0, n - 1);
    return 0;
}


