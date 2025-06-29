% sample_clock_offset.m
%
% David Rowe June 2017
%
% To simulate a sample clock offset we resample by a small amount
% using linear interpolation

function rx = sample_clock_offset(tx, sample_clock_offset_ppm)
  tin=1;
  tout=1;
  rx = zeros(1,length(tx));
  while tin < length(tx)
      t1 = floor(tin);
      t2 = ceil(tin);
      f = tin - t1;
      rx(tout) = (1-f)*tx(t1) + f*tx(t2);
      tout += 1;
      tin  += 1+sample_clock_offset_ppm/1E6;
  end
end
  
