// Created by Rupesh Choudhary on 6/26/2016.
/* Given a positive integer n, break it into the sum of at least two positive integers and maximize the
 * product of those integers. Return the maximum product you can get.
 * For example, given n = 2, return 1 (2 = 1 + 1); given n = 10, return 36 (10 = 3 + 3 + 4).
 */

#include<iostream>
using namespace std;

int integerBreaker(int key)
{
    int temp[key];
    temp[0] = 0;
    temp[1] = 1;
    temp[2] = 1;
    temp[3] = 2;
    temp[4] = 4;
    temp[5] = 6;
    temp[6] = 9;
    if (key < 7) return temp[key];
    for (int i = 7; i <= key; i++)
        temp[i] = 3 * temp[i-3];
    return temp[key];
}

int main()
{
    cout<<integerBreaker(10);
}


