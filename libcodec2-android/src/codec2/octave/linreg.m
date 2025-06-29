% linreg.m
% David Rowe April 2015
%
% Based on:
%    http://stackoverflow.com/questions/5083465/fast-efficient-least-squares-fit-algorithm-in-c
%
% finds y = mx + b to best fit n points x and y

function [m b] = linreg(x,y,n)
  sumx = 0.0;   % sum of x
  sumx2 = 0.0;  % sum of x^2
  sumxy = 0.0;  % sum of x * y
  sumy = 0.0;   % sum of y
  sumy2 = 0.0;  % sum of y**2

  for i=1:n   
    sumx  += x(i);       
    sumx2 += x(i)^2;  
    sumxy += x(i) * y(i);
    sumy  += y(i);      
    sumy2 += y(i)^2; 
  end 

  denom = (n * sumx2 - sumx*sumx);

  if denom == 0
    % singular matrix. can't solve the problem.
    m = 0;
    b = 0;
  else
    m = (n * sumxy  -  sumx * sumy) / denom;
    b = (sumy * sumx2  -  sumx * sumxy) / denom;
  end

endfunction
