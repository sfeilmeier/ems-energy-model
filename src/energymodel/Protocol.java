package energymodel;

public enum Protocol {
	HTTPS("https"), HTTP("http");

	public final String expression;

	private Protocol(String expression) {
		this.expression = expression;
	}
}
