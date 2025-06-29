% autotest.m
% David Rowe Mar 2015
%
% Helper functions to plot output of C verson and difference between Octave and C versions

1;

function stem_sig_and_error(plotnum, subplotnum, sig, error, titlestr, axisvec)
  global no_plot_list;

  if find(no_plot_list == plotnum)
    return;
  end
  
  figure(plotnum)
  subplot(subplotnum)
  stem(sig,'g;Octave version;');
  hold on;
  stem(error,'r;Octave - C version (hopefully 0);');
  hold off;
  if nargin == 6
    axis(axisvec);
  end
  title(titlestr);
endfunction


function plot_sig_and_error(plotnum, subplotnum, sig, error, titlestr, axisvec)
  global no_plot_list;

  if find(no_plot_list == plotnum)
    return;
  end
  
  figure(plotnum)
  subplot(subplotnum)
  plot(sig,'g;Octave version;');
  hold on;
  plot(error,'r;Octave - C version (hopefully 0);');
  hold off;
  if nargin == 6
    axis(axisvec);
  end
  title(titlestr);
endfunction


function pass = check(a, b, test_name, tol, its_an_angle = 0)
  global passes;
  global fails;

  if nargin == 3
    tol = 1E-3;
  end

  [m n] = size(a);
  if m > n
    ll = m;
  else
    ll = n;
  end

  printf("%s", test_name);
  for i=1:(25-length(test_name))
    printf(".");
  end
  printf(": ");  
  
  if its_an_angle
    % take into account pi is close to -pi for angles in rads
    e = sum(sum(abs(exp(j*a) - exp(j*b)))/ll);
  else
    e = sum(sum(abs(a - b))/ll);
  end

  if e < tol
    printf("OK\n");
    pass = true;
    passes++;
  else
    printf("FAIL (%f)\n",e);
    pass = false;
    fails++;
  end
endfunction

function pass = check_no_abs(a, b, test_name)
  global passes;
  global fails;

  tol = 1E-3;

  [m n] = size(a);
  if m > n
    ll = m;
  else
    ll = n;
  end

  printf("%s", test_name);
  for i=1:(25-length(test_name))
    printf(".");
  end
  printf(": ");  
  
  e = sum(sum(a - b)/ll);

  if e < tol
    pass = true;
    printf("OK\n");
    passes++;
  else
    pass = false;
    printf("FAIL (%f)\n",e);
    fails++;
  end
endfunction


