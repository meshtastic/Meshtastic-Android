% qam16.m
% David Rowe May 2020
%
% Octave QAM16 functions

1;

function symbol = qam16_mod(constellation, four_bits)
    bits_decimal = sum(four_bits .* [8 4 2 1]);
    symbol = constellation(bits_decimal+1);
endfunction

function four_bits = qam16_demod(constellation, symbol, amp_est=1)
    assert (amp_est != 0);
    symbol /= amp_est;
    dist = abs(symbol - constellation(1:16));
    [tmp decimal] = min(dist);
    four_bits = zeros(1,4);
    for i=1:4
      four_bits(1,5-i) = bitand(bitshift(decimal-1,1-i),1);
    end
endfunction

function test_qam16_mod_demod(constellation)
    for decimal=0:15
      tx_bits = zeros(1,4);
      for i=1:4
        tx_bits(1,5-i) = bitand(bitshift(decimal-1,1-i),1);
      end
      symbol = qam16_mod(constellation, tx_bits);
      rx_bits = qam16_demod(constellation,symbol);
      assert(tx_bits == rx_bits);
    end
endfunction

