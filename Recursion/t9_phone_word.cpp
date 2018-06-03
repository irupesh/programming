//
// Created by Rupesh Choudhary on 8/24/2016.
//
#include<iostream>
using namespace std;

string hashMap[]={"","","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ"};

void printCombination(string input,string result,int curr,int end){
    if(curr == end)
        cout<<result<<endl;
    else {
            for (int j = 0; j < hashMap[input[curr]-'0'].length(); j++) {
                if(input[curr]=='0' || input[curr]=='1')
                    continue;
                result[curr] = hashMap[input[curr]-'0'][j];
                printCombination(input, result, curr + 1, end);

            }
        }
}

int main(){
    printCombination("278","000",0, 3);
    return 0;
}


