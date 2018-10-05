import com.softwire.dynamite.bot.Bot;
import com.softwire.dynamite.game.Gamestate;
import com.softwire.dynamite.game.Move;
import com.softwire.dynamite.game.Round;

import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BOTBOT2 implements Bot {
	private Gamestate gamestate;
	private Random r;
	private int roundIndex;

	private Move toPlay;

	private int myDynamite;
	private int theirDynamite;

	private Move myLastPlay;
	private Move myCurrentPlay;
	private Move theirLastPlay;
	private Move theirCurrentPlay;

	// weighted map between my previous plays and their next play
	// then use my previous play to predict what they will play next
	// and counter it
	// { their previous move : [their current move, age]}
	private HashMap<Move, ArrayList<PreviousPlay>> weightings;
	// key gets beaten by value
	private HashMap<Move, ArrayList<Move>> rankings;

	private HashMap<Move, ArrayList<Move>> solidRankings;

	private static final int NUM_PREDICTIONS = 100;
	private ArrayList<Move> predictions;
	private HashMap<Move, Double> predictionAccuracy;
	private Move prediction;

	private boolean alreadyRemovedDynamite;

	public BOTBOT2() {
		this.r = new Random();

		this.roundIndex = 0;

		this.myDynamite = 100;
		this.theirDynamite = 100;

		this.alreadyRemovedDynamite = false;

		this.weightings = new HashMap<Move, ArrayList<PreviousPlay>>();

		this.rankings = new HashMap<Move, ArrayList<Move>>();
		this.rankings.put(Move.R, new ArrayList<Move>());
		this.rankings.get(Move.R).add(Move.P);
		this.rankings.get(Move.R).add(Move.P);
		this.rankings.get(Move.R).add(Move.P);
		this.rankings.get(Move.R).add(Move.D);
		this.rankings.put(Move.P, new ArrayList<Move>());
		this.rankings.get(Move.P).add(Move.S);
		this.rankings.get(Move.P).add(Move.S);
		this.rankings.get(Move.P).add(Move.S);
		this.rankings.get(Move.P).add(Move.D);
		this.rankings.put(Move.S, new ArrayList<Move>());
		this.rankings.get(Move.S).add(Move.R);
		this.rankings.get(Move.S).add(Move.R);
		this.rankings.get(Move.S).add(Move.R);
		this.rankings.get(Move.S).add(Move.D);
		this.rankings.put(Move.D, new ArrayList<Move>());
		this.rankings.get(Move.D).add(Move.W);
		this.rankings.put(Move.W, new ArrayList<Move>());
		this.rankings.get(Move.W).add(Move.R);
		this.rankings.get(Move.W).add(Move.P);
		this.rankings.get(Move.W).add(Move.S);

		this.solidRankings = new HashMap<Move, ArrayList<Move>>();
		this.solidRankings.put(Move.R, new ArrayList<Move>());
		this.solidRankings.get(Move.R).add(Move.P);
		this.solidRankings.get(Move.R).add(Move.D);
		this.solidRankings.put(Move.P, new ArrayList<Move>());
		this.solidRankings.get(Move.P).add(Move.S);
		this.solidRankings.get(Move.P).add(Move.D);
		this.solidRankings.put(Move.S, new ArrayList<Move>());
		this.solidRankings.get(Move.S).add(Move.R);
		this.solidRankings.get(Move.S).add(Move.D);
		this.solidRankings.put(Move.D, new ArrayList<Move>());
		this.solidRankings.get(Move.D).add(Move.W);
		this.solidRankings.put(Move.W, new ArrayList<Move>());
		this.solidRankings.get(Move.W).add(Move.R);
		this.solidRankings.get(Move.W).add(Move.P);
		this.solidRankings.get(Move.W).add(Move.S);
	}

	public Move makeMove(Gamestate gamestate) {
		this.gamestate = gamestate;
		this.roundIndex = gamestate.getRounds().size();
		if (this.roundIndex > 1) {
			this.myLastPlay = gamestate.getRounds().get(this.roundIndex - 2).getP1();
			this.myCurrentPlay = gamestate.getRounds().get(this.roundIndex - 1).getP1();
			this.theirLastPlay = gamestate.getRounds().get(this.roundIndex - 2).getP2();
			this.theirCurrentPlay = gamestate.getRounds().get(this.roundIndex - 1).getP2();

			// make weightings dictionary for this play if it doesn't exist
			if (!this.weightings.containsKey(this.theirLastPlay)) {
				this.weightings.put(this.theirLastPlay, new ArrayList<PreviousPlay>());
			}

			// add this play to weightings dictionary
			this.weightings.get(this.theirLastPlay).add(new PreviousPlay(this.theirCurrentPlay, this.roundIndex));

			// add again if they won the current play
			// they're likely to have a strong inclination to respond with the same again
			if (this.solidRankings.get(this.myCurrentPlay).contains(this.theirCurrentPlay)) {
				this.weightings.get(this.theirLastPlay).add(new PreviousPlay(this.theirCurrentPlay, this.roundIndex));
			}

			if (this.theirCurrentPlay.equals(Move.D)) {
				this.theirDynamite--;
			}
		}

		// increase chance of using dynamite towards the end
		// initial chance = 1/3
		// increased chance = 3/5
		if (this.roundIndex > 1000 - this.myDynamite && this.myDynamite >= 1 && !alreadyRemovedDynamite) {
			this.toPlay = Move.D;
			this.rankings.get(Move.S).add(Move.D);
			this.rankings.get(Move.S).add(Move.D);
			this.rankings.get(Move.P).add(Move.D);
			this.rankings.get(Move.P).add(Move.D);
			this.rankings.get(Move.R).add(Move.D);
			this.rankings.get(Move.R).add(Move.D);
			this.alreadyRemovedDynamite = true;
		}

		try {
			this.toPlay = chooseBestPrediction();
		} catch (Exception e) {
			this.toPlay = randomMove();
		}

		// if I run out of dynamite, never play dynamite again
		if (this.myDynamite < 1) {
			this.rankings.get(Move.R).remove(Move.D);
			this.rankings.get(Move.R).remove(Move.D);
			this.rankings.get(Move.R).remove(Move.D);
			this.rankings.get(Move.P).remove(Move.D);
			this.rankings.get(Move.P).remove(Move.D);
			this.rankings.get(Move.P).remove(Move.D);
			this.rankings.get(Move.S).remove(Move.D);
			this.rankings.get(Move.S).remove(Move.D);
			this.rankings.get(Move.S).remove(Move.D);
		}

		// if they run out of dynamite, never play waterbombs again
		if (this.theirDynamite < 1) {
			this.rankings.get(Move.D).remove(Move.W);
		}

		// double check I have dynamite left before trying to play it
		if (this.toPlay.equals(Move.D)) {
			if (this.myDynamite < 1) {
				try {
					this.toPlay = chooseBestPrediction();
				} catch (Exception e) {
					this.toPlay = randomMove();
				}
			}
			this.myDynamite--;
		}

		return this.toPlay;
	}

	// test each prediction's accuracy against previous logged plays
	// make n predictions, choose the one with historically best accuracy
	private Move chooseBestPrediction() throws Exception {
		this.predictions = new ArrayList<Move>();
		for (int i = 0; i < NUM_PREDICTIONS; i++) {
			this.predictions.add(weightedPrediction());
		}

		// choose the best prediction based on historical accuracy
		this.prediction = bestPrediction();

		// get a random move that beats the prediction
		// random to reduce the chance of another thing learning this ones behaviour
		return this.rankings.get(this.prediction).get(r.nextInt(this.rankings.get(this.prediction).size()));
	}

	// hashmap between each of the predictions, and the number of times it was correct
	// then choose the best one
	// or weighted choose the best one
	private Move bestPrediction() throws Exception {
		this.predictionAccuracy = new HashMap<Move, Double>();
		
		for (Move prediction : this.predictions) {
			this.predictionAccuracy.put(prediction, 0.0);
		}
		
		//prediction was correct if:
		//	in cases that are the same as theirCurrentPlay:
		//		this prediction == what they actually played next
		for (Move prediction : this.predictions) {
			for (int i = 0; i < this.roundIndex - 1; i++) {
				if (this.gamestate.getRounds().get(i).getP1().equals(this.theirCurrentPlay)) {
					if (this.gamestate.getRounds().get(i+1).getP2().equals(prediction)) {
						this.predictionAccuracy.put(prediction, this.predictionAccuracy.get(prediction) + 1.0);
					}
				}
			}
		}
		
		//choose best prediction by weight
		return getRandomWeighted(this.predictionAccuracy);
		
//		Move currentBestMove = Move.R;
//		Double currentBestAccuracy = 0.0;
//		for (HashMap.Entry<Move, Double> entry : this.predictionAccuracy.entrySet()) {
//			if (entry.getValue() > currentBestAccuracy) {
//				currentBestAccuracy = entry.getValue();
//				currentBestMove = entry.getKey();
//			}
//		}
//		return currentBestMove;
	}

	private Move weightedPrediction() throws Exception {		
		HashMap<Move, Double> weightedList = new HashMap<Move, Double>();
		Double weight;
		
		//get a weighted list of opponents plays against my current play
		//ignore weightings against dynamite if they've run out
		this.weightings.get(this.theirCurrentPlay).removeIf(entry -> entry.move.equals(Move.D) && this.theirDynamite < 1 );
		for (PreviousPlay play : this.weightings.get(this.theirCurrentPlay)) {
			if (weightedList.containsKey(play.move)) {
				weight = weightedList.get(play.move);
			} else {
				weight = (double) 0;
			}
			double x = 10 * (this.roundIndex - play.roundPlayedIn) / this.roundIndex;
			weight += 1 - (1 / (1 + Math.pow(Math.E, 5-x)));
			weightedList.put(play.move, weight);
		}	

		return getRandomWeighted(weightedList);
	}

	private Move getRandomWeighted(HashMap<Move, Double> weightedList) throws Exception {
		ArrayList<Double> cumulativeWeighting = new ArrayList<Double>();
		ArrayList<Move> moves = new ArrayList<Move>();
		Double total = (double) 0;
		Double num;
		Move ret = randomMove();

		for (HashMap.Entry<Move, Double> entry : weightedList.entrySet()) {
			total += entry.getValue();
			cumulativeWeighting.add(total);
			moves.add(entry.getKey());
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
		int num = r.nextInt(3);
		switch (num) {
		case 0:
			return Move.R;
		case 1:
			return Move.P;
		default:
			return Move.S;
		}
	}
}
