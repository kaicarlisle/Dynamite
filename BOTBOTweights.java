import com.softwire.dynamite.bot.Bot;
import com.softwire.dynamite.game.Gamestate;
import com.softwire.dynamite.game.Move;

import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;

public class BOTBOT implements Bot {
	private Random r;
	private int roundIndex;
	
	private Move toPlay;
	
	private int myDynamite;
	private int theirDynamite;
	
	private Move myLastPlay;
	private Move myCurrentPlay;
	private Move theirCurrentPlay;
	
	//weighted map between my previous plays and their next play
	//then use my previous play to predict what they will play next
	//and counter it
	//{ My previous move : [their previous move, age]}
	private HashMap<Move, ArrayList<PreviousPlay>> weightings;
	//key gets beaten by value
	private HashMap<Move, ArrayList<Move>> rankings;
	
	private Move prediction;
	
	public BOTBOT() {
		this.r = new Random();
		
		this.roundIndex = 0;
		
		this.myDynamite = 100;
		this.theirDynamite = 100;
		
		this.weightings = new HashMap<Move, ArrayList<PreviousPlay>>();
		
		this.rankings = new HashMap<Move, ArrayList<Move>>();
		this.rankings.put(Move.R, new ArrayList<Move>());
		this.rankings.get(Move.R).add(Move.P);
		this.rankings.get(Move.R).add(Move.D);
		this.rankings.put(Move.P, new ArrayList<Move>());
		this.rankings.get(Move.P).add(Move.S);
		this.rankings.get(Move.P).add(Move.D);
		this.rankings.put(Move.S, new ArrayList<Move>());
		this.rankings.get(Move.S).add(Move.R);
		this.rankings.get(Move.S).add(Move.D);
		this.rankings.put(Move.D, new ArrayList<Move>());
		this.rankings.get(Move.D).add(Move.W);
		this.rankings.put(Move.W, new ArrayList<Move>());
		this.rankings.get(Move.W).add(Move.R);
		this.rankings.get(Move.W).add(Move.P);
		this.rankings.get(Move.W).add(Move.S);
	}

	public Move makeMove(Gamestate gamestate) {
		this.roundIndex = gamestate.getRounds().size();
		if (this.roundIndex > 1) {
			this.myLastPlay = gamestate.getRounds().get(this.roundIndex-2).getP1();
			this.myCurrentPlay = gamestate.getRounds().get(this.roundIndex-1).getP1();
			this.theirCurrentPlay = gamestate.getRounds().get(this.roundIndex-1).getP2();
			
			//add this play to weightings dictionary
			if (!this.weightings.containsKey(this.myLastPlay)) {
				this.weightings.put(this.myLastPlay, new ArrayList<PreviousPlay>());
			}
			this.weightings.get(this.myLastPlay).add(new PreviousPlay(this.theirCurrentPlay, this.roundIndex));			
			
			//add the play again to increase the weighting if it resulted in winning that play
			if (this.rankings.get(this.theirCurrentPlay).contains(this.myCurrentPlay)) {
				this.weightings.get(this.myLastPlay).add(new PreviousPlay(this.theirCurrentPlay, this.roundIndex));
			}
			
			if (this.theirCurrentPlay.equals(Move.D)) {
				this.theirDynamite --;
			}			
		}
		
		//use up all remaining dynamite at the end of the match
		if (this.roundIndex > 1000 - this.myDynamite && this.myDynamite >= 1) {
			this.toPlay = Move.D;
		} else {
			try {
				this.toPlay = weightedMove();
			} catch (Exception e) {
				this.toPlay = randomMove();
			}
		}
		
		//if i run out of dynamite, never play dynamite again
		if (this.myDynamite < 1) {
			this.rankings.get(Move.R).remove(Move.D);
			this.rankings.get(Move.P).remove(Move.D);
			this.rankings.get(Move.S).remove(Move.D);
		}
		
		//if they run out of dynamite, never play waterbombs again
		if (this.theirDynamite < 1) {
			this.rankings.get(Move.D).remove(Move.W);
		}
		
		//double check I have dynamite left before trying to play it
		if (this.toPlay.equals(Move.D)) {
			if (this.myDynamite < 1) {
				this.toPlay = randomMove();
			}
			this.myDynamite --;
		}		
		
		return this.toPlay;
	}
	
	private Move weightedMove() throws Exception {		
		HashMap<Move, Double> weightedList = new HashMap<Move, Double>();
		Double weight;
		Float x;
		
		//get a weighted list of opponents plays
		for (PreviousPlay play : this.weightings.get(this.myLastPlay)) {
			if (weightedList.containsKey(play.move)) {
				weight = weightedList.get(play.move);
			} else {
				weight = (double) 0;
				weightedList.put(play.move, weight);
			}
			x = (this.roundIndex - play.roundPlayedIn) / 4f;
			weight += (1/Math.sqrt(2*Math.PI))*Math.pow(Math.E,(-(x*x)/2f));
			weightedList.put(play.move, weight);
		}	

		try {
			this.prediction = getRandomWeighted(weightedList);
		} catch (Exception e) {
			this.prediction = randomMove();
		}
		
		//get a random move that beats the prediction
		//random to reduce the chance of another thing learning this ones behaviour
		return this.rankings.get(this.prediction).get(r.nextInt(this.rankings.get(this.prediction).size()));
	}
	
	private Move getRandomWeighted(HashMap<Move, Double> weightedList) {
		ArrayList<Double> cumulativeWeighting = new ArrayList<Double>();
		ArrayList<Move> moves = new ArrayList<Move>();
		Double total = (double) 0;
		Double num;
		Move ret = randomMove();
		
		for (HashMap.Entry<Move, Double> entry : weightedList.entrySet()) {
			cumulativeWeighting.add(total);
			moves.add(entry.getKey());
			total += entry.getValue();
		}
		
		num = r.nextFloat() * total;
		for (int i = 0; i < cumulativeWeighting.size(); i++) {
			if (cumulativeWeighting.get(i) > num) {
				ret = moves.get(i);
				break;
			}
		}
		
		return ret;
	}
	
	private Move randomMove() {
		int num = r.nextInt(4);
		switch(num) {
			case 0: return Move.R;
			case 1: return Move.P;
			case 2: return Move.S;
			default: return Move.W;
		}
	}
}
