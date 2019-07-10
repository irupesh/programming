/*
Is  Unique: Implement  an algorithm to determine if  a  string  has all unique characters. What if  you 
cannot use additional data structures? 
*/

#include <iostream>

using namespace std;

bool isUniqueString_n2(string text){
	
	int size = text.size();
	bool result = true; 
	
	for(int i = 0; i<size; i++){
		for(int j=i+1; j<size; j++){
			if(text[i] == text[j]){
				result = false;
				break;
			}
		if(!result)
			break;
		}
	}
	return result;
}

bool isUniqueString_o_n_space(string text){
	
	int hash[26] = {0};
	int size = text.size();
	bool result = true;
	
	for(int i = 0; i<size; i++){
		int index = (int)text[i] - 97;
		//cout << index << "\n";
		if(hash[index] == 0)
			hash[index] = 1;
		else{
			result = false;
			break;	
		}	
	}
	return result;
}

bool isUniqueString(string text){
	
	int checker = 0;
	int size = text.size();
	bool result = true;
	
	for(int i = 0; i<size; i++){
		int index = (int)text[i] - 97;
		if((checker & (1 << index)) != 0)
			return false;
		else
			checker = checker | (1 << index);	
	}
	return result;
}

int main(){
	string text;
	
	cout << "Enter a string:\n";
	cin >> text;
	isUniqueString(text) ? cout << "Unique" : cout << "Not Unique";
	return 0;
}
