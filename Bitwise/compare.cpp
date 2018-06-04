#include<iostream>

using namespace std;

int cmp ( int x, int y) {
	return (x > y) - (x < y);
}

int main(){
	cout<<cmp(5,5);
	return 0;
}