% vq_binary_switch.m
% David Rowe Sep 2021
%
% Experiments in making VQs robust to bit errors, this is an Octave
% implementation of [1].
%
% [1] Psuedo Gray Coding, Zeger & Gersho 1990

1;

% returns indexes of hamming distance 1 neighbours
function index_neighbours = distance_one_neighbours(N,k)
  log2N = log2(N);
  index_neighbours = [];
  for b=0:log2N-1
    index_neighbour = bitxor(k-1,2.^b) + 1;
    index_neighbours = [index_neighbours index_neighbour];
  end
end

% equation (33) of [1], for hamming distance 1
function c = cost_of_distance_one(vq, prob, k, verbose=0)
  [N K] = size(vq);
  log2N = log2(N);
  c = 0;
  for b=0:log2N-1
    index_neighbour = bitxor(k-1,2.^b) + 1;
    diff = vq(k,:) - vq(index_neighbour, :);
    dist = sum(diff*diff');
    c += prob(k)*dist;
    if verbose
      printf("k: %d b: %d index_neighbour: %d dist: %f prob: %f c: %f \n", k, b, index_neighbour, dist, prob(k), c);
    end
  end
endfunction

% equation (39) of [1]
function d = distortion_of_current_mapping(vq, prob, verbose=0)
  [N K] = size(vq);
  
  d = 0;
  for k=1:N
    c = cost_of_distance_one(vq, prob, k);
    d += c;
    if verbose
      printf("k: %2d c: %f d: %f\n", k, c, d);
    end
  end
endfunction

function [vq distortion] = binary_switching(vq, prob, max_iteration, fast_en=1)
  [N K] = size(vq);
  iteration = 0;
  i = 1;
  finished = 0;
  switches = 0;
  distortion0 = distortion_of_current_mapping(vq, prob)
  
  while !finished

    % generate a list A(i) of which vectors have the largest cost of bit errors
    c = zeros(1,N);
    for k=1:N
      c(k) = cost_of_distance_one(vq, prob, k);
    end
    [tmp A] = sort(c,"descend");
    
    % Try switching each vector with A(i)
    best_delta = 0;
    for j=2:N
      % we can't switch with ourself
      if j != A(i)
        if fast_en
          delta = -cost_of_distance_one(vq, prob, A(i)) - cost_of_distance_one(vq, prob, j);
	  n1 = [distance_one_neighbours(N,A(i)) distance_one_neighbours(N,j)];
	  n1(n1 == A(i)) = [];
	  n1(n1 == j) = [];
	  for l=1:length(n1)
	    delta -= cost_of_distance_one(vq, prob, n1(l));
	  end
        else
	  distortion1 = distortion_of_current_mapping(vq, prob);
	end
	
        % switch vq entries A(i) and j
	tmp = vq(A(i),:);
	vq(A(i),:) = vq(j,:);
	vq(j,:) = tmp;
		
        if fast_en
	  delta += cost_of_distance_one(vq, prob, A(i)) + cost_of_distance_one(vq, prob, j);
	  for l=1:length(n1)
	    delta += cost_of_distance_one(vq, prob, n1(l));
	  end
	else
	  distortion2 = distortion_of_current_mapping(vq, prob);
          delta = distortion2 - distortion1;
	end
	
	if delta < 0
	  if abs(delta) > best_delta
	    best_delta = abs(delta);
	    best_j = j;
	  end
	end
	
	% unswitch
	tmp = vq(A(i),:);
	vq(A(i),:) = vq(j,:);
	vq(j,:) = tmp;
      end
    end % next j

    % printf("best_delta: %f best_j: %d\n", best_delta, best_j);
    if best_delta == 0
      % Hmm, no improvement, lets try the next vector in the sorted cost list
      if i == N
        finished = 1;
      else
        i++;
      end
    else
      % OK keep the switch that minimised the distortion
 
      tmp = vq(A(i),:);
      vq(A(i),:) = vq(best_j,:);
      vq(best_j,:) = tmp;
      switches++;

      % set up for next iteration
      iteration++;
      distortion = distortion_of_current_mapping(vq, prob);
      printf("it: %3d dist: %f %3.2f i: %3d sw: %3d\n", iteration, distortion,
             distortion/distortion0, i, switches);
      if iteration >= max_iteration, finished = 1, end
      i = 1;
     end

  end

endfunction

% return indexes of hamming distance one vectors
function ind = neighbour_indexes(vq, k)
  [N K] = size(vq);
  log2N = log2(N);
  ind = [];
  for b=0:log2N-1
    index_neighbour = bitxor(k-1,2.^b) + 1;
    ind = [ind index_neighbour];
  end
endfunction

function test_binary_switch
  vq1 = [1 1; -1 1; -1 -1; 1 -1];
  %f=fopen("vq1.f32","wb"); fwrite(f, vq1, 'float32'); fclose(f);
  [vq2 distortion] = binary_switching(vq1, ones(1,4), 10);
  % algorithm should put hamming distance 1 neighbours in adjacent quadrants
  distance_to_closest_neighbours = 2;
  % there are two hamming distance 1 neighbours
  target_distortion = 2^2*distance_to_closest_neighbours*length(vq1);
  assert(target_distortion == distortion);
  printf("test_binary_switch OK!\n");
endfunction

function test_fast
  N=16;     % Number of VQ codebook vectors
  K=2;      % Vector length
  Ntrain=10000;

  training_data = randn(Ntrain,K);
  [idx vq1] = kmeans(training_data, N);
  f=fopen("vq1.f32","wb");
  for r=1:rows(vq1)
    fwrite(f,vq1(r,:),"float32");
  end
  fclose(f);
  [vq2 distortion] = binary_switching(vq1, [1 ones(1,N-1)], 1000, fast_en = 0);
  [vq3 distortion] = binary_switching(vq1, [1 ones(1,N-1)], 1000, fast_en = 1);
  assert(vq2 == vq3);  
  printf("test_fast OK!\n");
endfunction

function demo
  N=16;     % Number of VQ codebook vectors
  K=2;      % Vector length
  Ntrain=10000;
  training_data = randn(Ntrain,K);
  [idx vq1] = kmeans(training_data, N);
  [vq2 distortion] = binary_switching(vq1, [1 ones(1,N-1)], 1000, 1);

  figure(1); clf; plot(training_data(:,1), training_data(:,2),'+');
  hold on;
  plot(vq1(:,1), vq1(:,2),'og','linewidth', 2);
  plot(vq2(:,1), vq2(:,2),'or','linewidth', 2);

  % plot hamming distance 1 neighbours
  k = 1;
  ind = neighbour_indexes(vq2, k);
  for i=1:length(ind)
    plot([vq2(k,1) vq2(ind(i),1)],[vq2(k,2) vq2(ind(i),2)],'r-','linewidth', 2);
  end
  hold off;
endfunction

pkg load statistics
%test_binary_switch;
test_fast;
%demo

