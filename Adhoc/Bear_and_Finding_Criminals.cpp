/*
There are n cities in Bearland, numbered 1 through n. Cities are arranged in one long row. The distance between cities i and j is equal to |i - j|.

Limak is a police officer. He lives in a city a. His job is to catch criminals. It's hard because he doesn't know in which cities criminals are. Though, he knows that there is at most one criminal in each city.

Limak is going to use a BCD (Bear Criminal Detector). The BCD will tell Limak how many criminals there are for every distance from a city a. After that, Limak can catch a criminal in each city for which he is sure that there must be a criminal.

You know in which cities criminals are. Count the number of criminals Limak will catch, after he uses the BCD.

Input
The first line of the input contains two integers n and a (1 ≤ a ≤ n ≤ 100) — the number of cities and the index of city where Limak lives.

The second line contains n integers t1, t2, ..., tn (0 ≤ ti ≤ 1). There are ti criminals in the i-th city.

Output
Print the number of criminals Limak will catch.

Examples
inputCopy
6 3
1 1 1 0 1 0
outputCopy
3
inputCopy
5 2
0 0 0 1 0
outputCopy
1

*/

//http://codeforces.com/problemset/problem/680/B

#include<iostream>

using namespace std;

int main(){
    int no_city,pos;
    cin>>no_city>>pos;
    int arr[no_city];
    int count = 0;
    int lowerLimit = pos-1;
    int upperLimit = pos-1;

    for(int i=0;i<no_city;i++)
      cin>>arr[i];

    while(lowerLimit >= 0 && upperLimit < no_city){
      if(arr[lowerLimit] == 1 && arr[upperLimit] == 1){
        if(lowerLimit == upperLimit)
          count ++;
        else
          count += 2;
      }
      lowerLimit = lowerLimit - 1;
      upperLimit = upperLimit + 1;
    }

    while(lowerLimit >= 0){
      if(arr[lowerLimit] == 1)
        count++;
      lowerLimit--;
    }

    while(upperLimit < no_city){
        if(arr[upperLimit] == 1)
          count++;
        upperLimit++;
    }

    cout << count << '\n';

}
