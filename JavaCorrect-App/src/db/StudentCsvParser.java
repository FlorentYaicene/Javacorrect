package db;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StudentCsvParser {
	private static final String SEPARATOR = ",";
	private String line = "";
	private String className = null;
	private int classYear = -1;
	private boolean promotionChecked = false;
	private int idPromotion = -1;
	
	/**
	 * Reads a CSV file.
	 * the 1st column contains the student numbers</br>
	 * the 2nd column contains the first name of the student at this line</br>
	 * the 3rd column contains the last name of the student at this line</br>
	 * the 4th column contains the class name of the student at this line</br>
	 * the 5th column contains the class year of the student at this line
	 * </br>
	 * All students with a different class name and class year from the first
	 * student in list are ignored.
	 *
	 * @param csvFile the file to read
	 */
	public void parse(final String csvFile) {
		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
			while ((line = br.readLine()) != null) {
				final String[] students = line.split(SEPARATOR);
				final int studentNum = Integer.parseInt(students[0]);
				final String studentFirstName = students[1];
				final String studentLastName = students[2];
				
				if (className == null) {
					className = students[3];
				}
				if (classYear == -1) {
					classYear = Integer.parseInt(students[4]);
				}
				if (!students[3].equals(className) || !(Integer.parseInt(students[4]) == classYear)) {
					System.err.println("Warning : l'étudiant " + studentFirstName + " " + studentLastName + " n'a pas pu être ajouté" + " car sa classe ou promotion est différente de celle des autres");
					System.out.println(classYear);
					continue;
				}
				try {
					if (!promotionChecked) {
						ResultSet rspromo = MysqlRequest.getIdPromotionRequest(classYear, className);
						// if is not before first, then class name of classe year doesn't exists in database

						if (!rspromo.isBeforeFirst()) {
							ResultSet rsidClasse = MysqlRequest.getidClasseRequest(className);
							if (!rsidClasse.isBeforeFirst()) {
								MysqlRequest.insertClasse(className);
								rsidClasse = MysqlRequest.getidClasseRequest(className);
							}
							rsidClasse.next();
							final int idClasse = rsidClasse.getInt("idClasse");
							MysqlRequest.insertPromotion(classYear, idClasse);
						}
						rspromo = MysqlRequest.getIdPromotionRequest(classYear, className);
						rspromo.next();
						idPromotion = rspromo.getInt("idPromotion");
						promotionChecked = true;
					}
					final ResultSet rstudent = MysqlRequest.getStudentByNum(studentNum);
					if (!rstudent.isBeforeFirst()) {
						MysqlRequest.insertStudent(studentNum, studentFirstName, studentLastName, idPromotion);
					}
				} catch (final SQLException ex) {
					System.out.println("SQLException: " + ex.getMessage());
					System.out.println("SQLState: " + ex.getSQLState());
					System.out.println("SQLState: " + ex.toString());
					System.out.println("VendorError: " + ex.getErrorCode());
					System.out.println(idPromotion);
				}
			}
		} catch (final FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
