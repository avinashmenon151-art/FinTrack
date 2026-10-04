package model;

/**
 * Model representing the 'categories' entity in FinTrack.
 * Shared system categories have userId == null.
 */
public class Category {

    private Integer categoryId;
    private Integer userId;
    private String categoryName;
    private CategoryType categoryType;
    private String description;

    public Category() {
    }

    public Category(Integer categoryId, Integer userId, String categoryName,
                    CategoryType categoryType, String description) {
        this.categoryId = categoryId;
        this.userId = userId;
        this.categoryName = categoryName;
        this.categoryType = categoryType;
        this.description = description;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public CategoryType getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(CategoryType categoryType) {
        this.categoryType = categoryType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Category{" +
                "categoryId=" + categoryId +
                ", userId=" + userId +
                ", categoryName='" + categoryName + '\'' +
                ", categoryType=" + categoryType +
                ", description='" + description + '\'' +
                '}';
    }
}
