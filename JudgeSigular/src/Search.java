import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class Search {
	private static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	private static String[] options = { "-maxLength", "80",
			"-retainTmpSubcategories" };
	private static String[] VBList = { "VB", "VBD", "VBG", "VBN", "VBP", "VBZ" };
	private static String[] NNList = { "PRP", "PRP$", "NN", "NNS", "NNP",
			"NNPS", "DT" };
	private static String[] ThirdList = { "he", "she", "it", "this", "that" };
	private static String[] beList = { "is", "am", "are" };
	private static LexicalizedParser lp;
	private static TreebankLanguagePack tlp;

	public static void main(String[] args) throws IOException {
		// ʹ�������в�������ȡ��������ļ���
		if (args.length != 2) {
			System.out
					.println("Please run the program as follow : java search test.input test.output");
		} else {
			// ��ʼ���﷨������
			lp = LexicalizedParser.loadModel(parserModel, options);
			tlp = lp.getOp().langpack();

			// ��������ļ�������������
			BufferedReader br = new BufferedReader(new FileReader(new File(
					args[0])));

			// ��������ļ������������
			BufferedWriter bf = new BufferedWriter(new FileWriter(new File(
					args[1])));

			String oneline;
			while ((oneline = br.readLine()) != null) {
				bf.write(searchWrong(oneline));
				bf.newLine();
			}

			br.close();
			bf.close();
		}
	}

	// ��ÿһ�����ӽ��д���
	private static String searchWrong(String oneline) {
		String toReturn = oneline;
		Tokenizer<? extends HasWord> toke = tlp.getTokenizerFactory()
				.getTokenizer(new StringReader(oneline));
		List<? extends HasWord> sentence = toke.tokenize();
		Tree parse = lp.parse(sentence);

		// ��ӡ��parse��
		parse.pennPrint();
		System.out.println();

		// �ҵ������еĴӾ�
		List<Tree> subs = subs(parse, null);

		// ���ڴ�Ŵ������Ҷ�ڵ�
		List<Tree> wrongs = new ArrayList<Tree>();

		// ��ÿ���Ӿ���д������ݴ淵�صĴ������
		for (Tree tree : subs) {
			tree.pennPrint();
			Tree temp = judgeSingular(tree);
			if (temp != null) {
				wrongs.add(temp);
			}
		}

		String num = "";
		if (wrongs.size() == 0) {
			num = "-1 ";
		} else {
			List<Tree> leaves = parse.getLeaves();
			for (Tree leaf : wrongs) {
				for (int i = 0; i < leaves.size(); i++) {
					if (leaves.get(i) == leaf) {
						num = num + (i + 1) + " ";
						break;
					}
				}
			}
		}

		toReturn = num + toReturn;

		// ��ӡ�����
		System.out.println(toReturn);
		return toReturn;
	}

	// �÷���Ӧ���и����ŵ�д������һ�α����У����ɽ�������Ҫ�������˳Ƶ�S,SBAR,SBARQ,SINV���ҳ�
	private static List<Tree> subs(Tree tree, Tree parent) {
		List<Tree> toReturn = new ArrayList<Tree>();
		if (tree.isLeaf()) {
			return toReturn;
		} else {
			if (tree.value().equals("S")) {// �˴�������������͵��ж�
				toReturn.add(tree);
			} else if (tree.value().equals("SBAR")) {
				toReturn.add(parent);
			}
			for (int i = 0; i < tree.getChildrenAsList().size(); i++) {
				toReturn.addAll(subs(tree.getChildrenAsList().get(i), tree));
			}
			return toReturn;
		}
	}

	// �жϴӾ����Ƿ���ڵ����˳Ƶ�������
	private static Tree judgeSingular(Tree tree) {
		Tree toReturn = null;
		if (tree.value().equals("S")) {
			toReturn = judgeForS(tree);
		} else {// �˴�������������͵��ж�
			toReturn = judgeForSBAR(tree);
		}
		return toReturn;
	}

	// ����һ�������
	private static Tree judgeForS(Tree tree) {
		Tree toReturn = null;
		Tree[] children = tree.children();
		Tree np = null;
		Tree vp = null;
		Tree nn = null;
		Tree vb = null;
		// �ҵ�np��vp,md
		for (int i = 0; i < children.length; i++) {
			if (children[i].value().equals("NP")) {
				np = children[i];
			}
			if (children[i].value().equals("VP")) {
				vp = children[i];
			}
		}
		// �������Ϊ�գ���˵�����Ӳ�������ֱ�ӷ���
		if (np == null || vp == null) {
			return null;
		}
		// �ҵ�np�е�NN
		nn = findNN(np);
		// �ҵ�vp�е�VB
		for (int i = 0; i < vp.children().length; i++) {
			Tree temp = vp.children()[i];
			if (vb != null) {
				break;
			}
			for (String m : VBList) {
				if (temp.value().equals(m)) {
					vb = temp;
					break;
				}
			}
		}

		// ��ȥ��̬���ʺ�TO��������Լ����β��Ե����
		if (vp.firstChild().value().equals("MD")
				|| vp.firstChild().value().equals("TO") || vb == null
				|| nn == null) {
			return null;
		}
		if (isSingleNN(nn) != isSingleVB(vb) && !isOther(vb)) {
			toReturn = vb;
		} else {
			// �ڴ˴����ǵ�һ�˳�Ϊ��������
			if (nn.firstChild().value().toLowerCase().equals("i") != vb
					.firstChild().value().toLowerCase().equals("am")
					&& isBe(vb.firstChild())) {
				toReturn = vb;
			}
		}
		if (toReturn != null) {
			toReturn = toReturn.firstChild();
		}
		return toReturn;
	}

	private static boolean isBe(Tree tree) {
		String temp = tree.value().toLowerCase();
		for (String s : beList) {
			if (s.equals(temp)) {
				return true;
			}
		}
		return false;
	}

	// �ҵ�np�Ӿ��е�nn
	private static Tree findNN(Tree np) {
		Tree nn = null;
		for (int i = 0; i < np.children().length; i++) {
			Tree temp = np.children()[i];
			if (nn != null) {
				break;
			}
			// ����np��ÿ�����ʣ����Ƿ���nnlist�У��Ӷ��ҳ�nn
			for (String m : NNList) {
				if (temp.value().equals(m)) {
					// ��ǰnnΪthe���޷��ж�nn�ĵ�����ʱ������һλ
					if (temp.firstChild().value().toLowerCase().equals("the")) {
						continue;
					}
					nn = temp;
					break;
				}
			}
		}
		if (nn == null) {
			// ��û��nn��np�»���npʱ������Ѱ��np�µ�np�д��ڵ�nn
			for (int i = 0; i < np.children().length; i++) {
				Tree temp = np.children()[i];
				if (nn != null) {
					break;
				}
				if (temp.value().equals("NP")) {
					nn = findNN(temp);
				}
			}
		}
		return nn;
	}

	// �жϵ��������˳�
	private static boolean isSingleNN(Tree nn) {
		String value = nn.value();
		if (value.equals("NN") || value.equals("NNP")
				|| isThird(nn.firstChild().value())) {
			return true;
		}
		return false;
	}

	// �ж϶�����ʽ
	private static boolean isSingleVB(Tree vb) {
		String value = vb.value();
		if (value.equals("VBZ")) {
			return true;
		}
		return false;
	}

	// �����˳ƴ��ʣ��Ƿ�Ϊ�����˳�
	private static boolean isThird(String toCheck) {
		toCheck = toCheck.toLowerCase();
		for (String m : ThirdList) {
			if (toCheck.equals(m)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOther(Tree tree) {
		if (tree.value().equals("VBD") || tree.value().equals("VBG")
				|| tree.value().equals("VBN")) {
			return true;
		}
		return false;
	}

	// ���ڴ����������Ӿ�
	private static Tree judgeForSBAR(Tree tree) {
		Tree toReturn = null;
		Tree[] children = tree.children();
		Tree sbar = null;
		Tree np = null;
		Tree vp = null;
		Tree nn = null;
		Tree vb = null;
		// �ҵ�np��vp,md
		for (int i = 0; i < children.length; i++) {
			if (children[i].value().equals("NP")) {
				np = children[i];
			}
			if (children[i].value().equals("SBAR")) {
				sbar = children[i];
			}
		}
		// �������Ϊ�գ���˵�����Ӳ�������ֱ�ӷ���
		if (np == null || sbar == null) {
			return null;
		}
		// �ҵ�np�е�NN
		nn = findNN(np);

		// �ҵ�sbar�µ�vp
		vp = findVP(sbar);
		if (vp == null) {
			return null;
		}

		// �ҵ�vp�е�vb
		for (int i = 0; i < vp.children().length; i++) {
			Tree temp = vp.children()[i];
			if (vb != null) {
				break;
			}
			for (String m : VBList) {
				if (temp.value().equals(m)) {
					vb = temp;
					break;
				}
			}
		}

		// ��ȥ��̬���ʺ�TO��������Լ����β��Ե����
		if (vp.firstChild().value().equals("MD")
				|| vp.firstChild().value().equals("TO") || vb == null
				|| nn == null) {
			return null;
		}

		if (isSingleNN(nn) != isSingleVB(vb) && !isOther(vb)) {
			toReturn = vb;
		} else {
			// �ڴ˴����ǵ�һ�˳�Ϊ��������
			if (nn.firstChild().value().toLowerCase().equals("i") != vb
					.firstChild().value().toLowerCase().equals("am")
					&& isBe(vb.firstChild())) {
				toReturn = vb;
			}
		}
		if (toReturn != null) {
			toReturn = toReturn.firstChild();
		}

		return toReturn;
	}

	private static Tree findVP(Tree sbar) {
		Tree vp = null;
		for (int i = 0; i < sbar.children().length; i++) {
			Tree temp = sbar.children()[i];
			if (temp.value().equals("VP")) {
				vp = temp;
			}
		}
		if (vp == null) {
			for (int i = 0; i < sbar.children().length; i++) {
				Tree temp = sbar.children()[i];
				if (!temp.isLeaf()) {
					Tree result = findVP(temp);
					if (result != null) {
						vp = result;
						break;
					}
				}
			}
		}
		return vp;
	}
}
