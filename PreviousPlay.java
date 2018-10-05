import com.softwire.dynamite.game.Move;

public class PreviousPlay {
	public Move move;
	public Integer roundPlayedIn;
	
	public PreviousPlay(Move move, Integer round) {
		this.move = move;
		this.roundPlayedIn = round;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Move) {
			PreviousPlay b = (PreviousPlay) o;
			return this.move.equals(b.move);
		} else {
			return false;
		}
	}
}
