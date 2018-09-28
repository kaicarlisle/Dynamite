import com.softwire.dynamite.game.Move;

public class PreviousPlay {
	public Move move;
	public Integer roundPlayedIn;
	
	public PreviousPlay(Move move, Integer round) {
		this.move = move;
		this.roundPlayedIn = round;
	}
}
