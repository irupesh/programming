/*
Let's consider a table consisting of n rows and n columns. The cell located at the intersection of i-th row and j-th column contains number i × j. The rows and columns are numbered starting from 1.
You are given a positive integer x. Your task is to count the number of cells in a table that contain number x.

Input
The single line contains numbers n and x (1 ≤ n ≤ 105, 1 ≤ x ≤ 109) — the size of the table and the number that we are looking for in the table.

Output
Print a single number: the number of times x occurs in the table.

Examples
inputCopy
10 5
outputCopy
2
inputCopy
6 12
outputCopy
4
inputCopy
5 13
outputCopy
0
*/

#include<iostream>

using namespace std;

int main(){
	int width, key;
	int count = 0;
	cin>>width>>key;

	for(int i=1;i<=width;i++){
		if(key%i == 0 && key/i <= width)
			count++;
	}

	cout<<count;
}
